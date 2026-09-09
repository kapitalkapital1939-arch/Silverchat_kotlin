package com.silverchat.core.testing

import com.silverchat.core.common.dispatcher.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Заменяет Dispatchers.Main на тестовый диспетчер.
 *
 * Без этого правила любая ViewModel с viewModelScope падает в юнит-тестах с
 * «Module with the Main dispatcher had failed to initialize».
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    private val testDispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher() {

    val dispatcher: TestDispatcher get() = testDispatcher

    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

/**
 * Тестовый [DispatcherProvider]: все диспетчеры — один TestDispatcher.
 *
 * Это делает тесты детерминированными: порядок выполнения не зависит от
 * планировщика потоков, поэтому тесты не «мигают» в CI.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TestDispatcherProvider(
    private val testDispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : DispatcherProvider {
    override val main: CoroutineDispatcher = testDispatcher
    override val io: CoroutineDispatcher = testDispatcher
    override val default: CoroutineDispatcher = testDispatcher
    override val unconfined: CoroutineDispatcher = testDispatcher
    override val realtime: CoroutineDispatcher = testDispatcher
    override val media: CoroutineDispatcher = testDispatcher
}
