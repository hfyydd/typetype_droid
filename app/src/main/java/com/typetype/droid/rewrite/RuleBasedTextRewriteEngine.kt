package com.typetype.droid.rewrite

class RuleBasedTextRewriteEngine : TextRewriteEngine {
    override fun rewrite(rawText: String): String = StructuredTextFormatter.rewrite(rawText)
}

