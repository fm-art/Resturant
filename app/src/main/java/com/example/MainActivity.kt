package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.ToastKind
import com.example.service.OrderMonitorService
import com.example.ui.StoreViewModel
import com.example.ui.components.PartnerToastBanner
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.PartnerConsoleScaffold
import com.example.ui.theme.GossaigaonStoreTheme
import com.example.util.OrderAlertManager

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        OrderAlertManager.ensureChannels(this)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )
        setContent {
            GossaigaonStoreTheme {
                val context = LocalContext.current
                val storeViewModel: StoreViewModel = viewModel()
                val uiState by storeViewModel.uiState.collectAsStateWithLifecycle()

                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { granted ->
                    if (!granted) {
                        storeViewModel.showToast(
                            "Enable Notifications in Settings to receive order alerts!",
                            ToastKind.WARNING
                        )
                    }
                }

                LaunchedEffect(uiState.isAuthenticated, uiState.uid) {
                    if (uiState.isAuthenticated && uiState.uid.isNotBlank()) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            val granted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) == PackageManager.PERMISSION_GRANTED
                            if (!granted) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                        OrderMonitorService.start(context, uiState.uid)
                    } else {
                        OrderAlertManager.stopLoudRinging(context)
                        OrderAlertManager.clearAllOrderNotifications(context)
                        OrderMonitorService.stop(context)
                    }
                }

                LaunchedEffect(uiState.soundEnabled) {
                    OrderAlertManager.setSoundEnabled(uiState.soundEnabled)
                    if (!uiState.soundEnabled) {
                        OrderAlertManager.stopLoudRinging(context)
                    }
                }

                LaunchedEffect(uiState.isAuthenticated, uiState.activeOrders) {
                    if (uiState.isAuthenticated) {
                        OrderAlertManager.syncActiveOrderNotifications(context, uiState.activeOrders)
                    }
                }

                LaunchedEffect(uiState.isAuthenticated, uiState.soundEnabled, uiState.ringingOrderCount) {
                    if (uiState.isAuthenticated && uiState.soundEnabled && uiState.ringingOrderCount > 0) {
                        OrderAlertManager.startLoudRinging(context)
                    } else {
                        OrderAlertManager.stopLoudRinging(context)
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    if (!uiState.isAuthenticated) {
                        AuthScreen(
                            isLoading = uiState.authLoading,
                            authError = uiState.authError,
                            authSuccessMessage = uiState.authSuccessMessage,
                            authLockoutSeconds = uiState.authLockoutSeconds,
                            pendingEmailVerification = uiState.pendingEmailVerification,
                            verificationEmail = uiState.verificationEmail,
                            resendCooldownSeconds = uiState.resendVerificationCooldownSeconds,
                            onClearError = { storeViewModel.clearAuthError() },
                            onLogin = { email, password ->
                                storeViewModel.login(email, password)
                            },
                            onRegister = { name, email, cuisine, addr, phone, upi, pass, lat, lng ->
                                storeViewModel.registerStore(
                                    name, email, cuisine, addr, phone, upi, pass, lat, lng
                                )
                            },
                            onGoogleSignInToken = { idToken ->
                                storeViewModel.signInWithGoogleIdToken(idToken)
                            },
                            onGoogleSignInError = { err ->
                                storeViewModel.reportGoogleSignInError(err)
                            },
                            onSendPasswordReset = { email, onDone ->
                                storeViewModel.sendPasswordReset(email, onDone)
                            },
                            onResendVerification = {
                                storeViewModel.resendVerificationEmail()
                            },
                            onCheckEmailVerified = {
                                storeViewModel.checkEmailVerification()
                            },
                            onSignOutVerification = {
                                storeViewModel.logout()
                            },
                            onToast = { msg, kind -> storeViewModel.showToast(msg, kind) }
                        )
                    } else {
                        PartnerConsoleScaffold(
                            state = uiState,
                            viewModel = storeViewModel
                        )
                    }

                    PartnerToastBanner(
                        toast = uiState.activeToast,
                        onDismiss = { storeViewModel.onToastTapped() },
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                    )
                }
            }
        }
    }
}
