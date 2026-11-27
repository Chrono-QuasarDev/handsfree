package com.handsfree.core.model

data class UssdScreen(
    val sourcePackage: String,
    val messageText: String,
    val options: List<Option> = emptyList(),
    val inputField: InputField? = null,
    val buttons: List<String> = emptyList()
)

data class Option(val index: Int?, val label: String)

/** hint only — never the entered text. */
data class InputField(val isPassword: Boolean, val hint: String?)
