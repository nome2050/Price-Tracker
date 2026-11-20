package com.pricetracker.app.core

import com.pricetracker.app.BuildConfig

/** Default Groq key, compiled in from `groq.key=...` in the git-ignored local.properties. Settings overrides it. */
object Secrets {
    val GROQ_KEY: String = BuildConfig.GROQ_KEY
}
