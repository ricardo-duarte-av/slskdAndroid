package com.slskdandroid.core.common

import javax.inject.Qualifier
import kotlin.annotation.AnnotationRetention.RUNTIME

/** Qualifier for an application-lifetime [kotlinx.coroutines.CoroutineScope]. */
@Qualifier
@Retention(RUNTIME)
annotation class ApplicationScope
