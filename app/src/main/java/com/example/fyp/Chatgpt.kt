package com.example.fyp

class Chatgpt {
    data class Gpt3Request(val prompt: String, val max_tokens: Int = 150)
    data class Gpt3Response(val choices: List<Gpt3Choice>)
    data class Gpt3Choice(val text: String)
}
