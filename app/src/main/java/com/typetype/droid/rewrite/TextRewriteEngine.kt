package com.typetype.droid.rewrite

fun interface TextRewriteEngine {
    fun rewrite(rawText: String): String
}

