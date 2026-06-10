package org.example.nabat.adapter.in.websocket;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AlertResponseWrapper(String type, Object alert) {}
