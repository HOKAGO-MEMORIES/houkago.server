package com.houkago.server.content.post;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.houkago.server.content.post.readmodel.PostReadModel;

class PostPackageArchitectureTest {

	private static final Path POST_SOURCE_ROOT = Path.of(
			"src/main/java/com/houkago/server/content/post");
	private static final Set<String> EXPECTED_PACKAGES = Set.of(
			"api",
			"asset",
			"checksum",
			"metadata",
			"policy",
			"preparation",
			"query",
			"readmodel",
			"source",
			"sync",
			"webhook");
	private static final Pattern PACKAGE_PATTERN = Pattern.compile(
			"^package com\\.houkago\\.server\\.content\\.post\\.([a-z][a-z0-9]*);$",
			Pattern.MULTILINE);
	private static final Pattern POST_IMPORT_PATTERN = Pattern.compile(
			"^import com\\.houkago\\.server\\.content\\.post\\.([a-z][a-z0-9]*)\\.[^;]+;$",
			Pattern.MULTILINE);

	@Test
	void postPackageTreeMatchesTheR4Contract() throws IOException {
		try (Stream<Path> entries = Files.list(POST_SOURCE_ROOT)) {
			Set<String> actualPackages = entries
					.filter(Files::isDirectory)
					.map(path -> path.getFileName().toString())
					.collect(java.util.stream.Collectors.toSet());

			assertThat(actualPackages).containsExactlyInAnyOrderElementsOf(EXPECTED_PACKAGES);
		}
	}

	@Test
	void preparationAndAssetKeepTheirDatabaseIndependentDependencyBoundaries() throws IOException {
		Map<String, Set<String>> dependencies = packageDependencies();

		assertThat(dependencies.getOrDefault("asset", Set.of())).doesNotContain("readmodel");
		assertThat(dependencies.getOrDefault("preparation", Set.of()))
				.doesNotContain("asset", "readmodel");

		String preparationSource = packageSource("preparation");
		assertThat(preparationSource)
				.doesNotContain("import jakarta.persistence")
				.doesNotContain("import org.springframework.data");
	}

	@Test
	void postPackageDependenciesHaveNoCycles() throws IOException {
		Map<String, Set<String>> dependencies = packageDependencies();

		for (String origin : dependencies.keySet()) {
			for (String dependency : dependencies.get(origin)) {
				assertThat(reaches(dependencies, dependency, origin, new HashSet<>()))
						.as("package dependency cycle from %s through %s", origin, dependency)
						.isFalse();
			}
		}
	}

	@Test
	void postReadModelMutationIsPackageControlledAndJpaConstructorIsProtected() throws NoSuchMethodException {
		assertThat(PostReadModel.class.getDeclaredMethods())
				.filteredOn(method -> method.getName().startsWith("set"))
				.hasSize(22)
				.allMatch(method -> !Modifier.isPublic(method.getModifiers())
						&& !Modifier.isProtected(method.getModifiers())
						&& !Modifier.isPrivate(method.getModifiers()));
		assertThat(PostReadModel.class.getConstructors()).isEmpty();
		assertThat(Modifier.isProtected(
				PostReadModel.class.getDeclaredConstructor().getModifiers())).isTrue();
	}

	private static Map<String, Set<String>> packageDependencies() throws IOException {
		Map<String, Set<String>> dependencies = new HashMap<>();
		for (Path sourceFile : javaSourceFiles()) {
			String source = Files.readString(sourceFile, StandardCharsets.UTF_8);
			Matcher packageMatcher = PACKAGE_PATTERN.matcher(source);
			assertThat(packageMatcher.find())
					.as("post package declaration in %s", sourceFile)
					.isTrue();
			String sourcePackage = packageMatcher.group(1);
			Set<String> importedPackages = dependencies.computeIfAbsent(sourcePackage, ignored -> new HashSet<>());

			Matcher importMatcher = POST_IMPORT_PATTERN.matcher(source);
			while (importMatcher.find()) {
				String importedPackage = importMatcher.group(1);
				if (!sourcePackage.equals(importedPackage)) {
					importedPackages.add(importedPackage);
				}
			}
		}
		return dependencies;
	}

	private static String packageSource(String packageName) throws IOException {
		StringBuilder source = new StringBuilder();
		for (Path sourceFile : javaSourceFiles()) {
			if (sourceFile.getParent().getFileName().toString().equals(packageName)) {
				source.append(Files.readString(sourceFile, StandardCharsets.UTF_8));
			}
		}
		return source.toString();
	}

	private static Set<Path> javaSourceFiles() throws IOException {
		try (Stream<Path> files = Files.walk(POST_SOURCE_ROOT)) {
			return files
					.filter(Files::isRegularFile)
					.filter(path -> path.getFileName().toString().endsWith(".java"))
					.collect(java.util.stream.Collectors.toUnmodifiableSet());
		}
	}

	private static boolean reaches(
			Map<String, Set<String>> dependencies,
			String current,
			String target,
			Set<String> visited) {
		if (current.equals(target)) {
			return true;
		}
		if (!visited.add(current)) {
			return false;
		}
		for (String dependency : dependencies.getOrDefault(current, Set.of())) {
			if (reaches(dependencies, dependency, target, visited)) {
				return true;
			}
		}
		return false;
	}
}
