package com.example.okta.serviceone;

import java.util.Map;

record TokenResult(String accessToken, Map<String, Object> request, Map<String, Object> response) {
}
