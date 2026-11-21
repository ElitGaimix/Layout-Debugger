package fr.eligaimix.ltdr.network;

import java.util.Objects;

/** Small intent packet. The server checks permissions and session ownership. */
public record EditorRequest(String operation, String value, int page, int order) {
	public EditorRequest {
		Objects.requireNonNull(operation);
		Objects.requireNonNull(value);
		if (!operation.matches("[a-z_]{1,24}") || value.length() > 128 || page < 0 || order < 0) {
			throw new IllegalArgumentException("Requête d'édition invalide.");
		}
	}
}
