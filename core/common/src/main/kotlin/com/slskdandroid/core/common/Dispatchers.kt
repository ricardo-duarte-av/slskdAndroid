package com.slskdandroid.core.common

import javax.inject.Qualifier
import kotlin.annotation.AnnotationRetention.RUNTIME

/** Qualifier for the IO [kotlinx.coroutines.CoroutineDispatcher]. */
@Qualifier
@Retention(RUNTIME)
annotation class IoDispatcher

/** Qualifier for the Default (CPU-bound) [kotlinx.coroutines.CoroutineDispatcher]. */
@Qualifier
@Retention(RUNTIME)
annotation class DefaultDispatcher
