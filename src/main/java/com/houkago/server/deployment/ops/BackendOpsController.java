package com.houkago.server.deployment.ops;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/deployments/ops")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "houkago.ops.webhook", name = "enabled", havingValue = "true")
public class BackendOpsController {

	private final BackendOpsReceiver receiver;

	public BackendOpsController(BackendOpsReceiver receiver) {
		this.receiver = receiver;
	}

	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Void> receive(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestBody byte[] rawBody) {
		BackendOpsResult result = receiver.receive(authorization, rawBody);
		if (result.status() == BackendOpsResult.Status.DUPLICATE) {
			return ResponseEntity.ok().build();
		}
		return ResponseEntity.accepted().build();
	}
}
