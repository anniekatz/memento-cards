package com.annie.memento

import androidx.compose.ui.window.ComposeUIViewController
import com.annie.memento.di.createAppGraph

fun MainViewController() = ComposeUIViewController { App(createAppGraph()) }
