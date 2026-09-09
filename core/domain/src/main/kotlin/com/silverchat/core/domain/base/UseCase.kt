package com.silverchat.core.domain.base

import com.silverchat.core.common.dispatcher.DispatcherProvider
import com.silverchat.core.common.result.ScResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * Базовые контракты UseCase'ов.
 *
 * Три формы:
 *  - [FlowUseCase]   — наблюдаемое состояние (список чатов, лента сторис);
 *  - [SuspendUseCase] — разовое действие с результатом (отправить сообщение);
 *  - [SyncUseCase]   — чистая синхронная логика без IO (валидация, подсчёт).
 *
 * Все операции идут на нужном диспетчере из [DispatcherProvider] — ViewModel
 * не должна думать о потоках.
 */
abstract class FlowUseCase<in P, R>(private val dispatchers: DispatcherProvider) {

    protected abstract fun execute(params: P): Flow<R>

    operator fun invoke(params: P): Flow<R> = execute(params).flowOn(dispatcher())

    protected open fun dispatcher(): CoroutineDispatcher = dispatchers.default
}

/** Flow без параметров. */
abstract class NoParamFlowUseCase<R>(private val dispatchers: DispatcherProvider) {

    protected abstract fun execute(): Flow<R>

    operator fun invoke(): Flow<R> = execute().flowOn(dispatcher())

    protected open fun dispatcher(): CoroutineDispatcher = dispatchers.default
}

abstract class SuspendUseCase<in P, R>(private val dispatchers: DispatcherProvider) {

    protected abstract suspend fun execute(params: P): ScResult<R>

    suspend operator fun invoke(params: P): ScResult<R> =
        withContext(dispatcher()) { execute(params) }

    protected open fun dispatcher(): CoroutineDispatcher = dispatchers.io
}

abstract class NoParamSuspendUseCase<R>(private val dispatchers: DispatcherProvider) {

    protected abstract suspend fun execute(): ScResult<R>

    suspend operator fun invoke(): ScResult<R> = withContext(dispatcher()) { execute() }

    protected open fun dispatcher(): CoroutineDispatcher = dispatchers.io
}

abstract class SyncUseCase<in P, R> {
    abstract operator fun invoke(params: P): R
}

/** Ошибки валидации формы — возвращаются в UI без обращения к сети. */
sealed interface ValidationError {
    data class Field(val field: String, val message: String) : ValidationError
    data class Global(val message: String) : ValidationError
}
