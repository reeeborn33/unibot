package org.example.shared.llm;

/**
 * Minimal contract for building OpenAI-compatible {@code tools} request JSON.
 */
public interface LlmToolSpec {

    /** OpenAI {@code function} object JSON (name, description, parameters). */
    String toolDefinitionJson();
}
