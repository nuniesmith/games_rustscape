package com.rustscape.client.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rustscape.client.ui.theme.RustscapeColors

/**
 * Login state for the login screen
 */
data class LoginState(
    val username: String = "",
    val password: String = "",
    val rememberMe: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val selectedTab: LoginTab = LoginTab.LOGIN
)

/**
 * Login screen tabs
 */
enum class LoginTab {
    LOGIN,
    REGISTER
}

/**
 * Registration state
 */
data class RegisterState(
    val username: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val acceptTerms: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

/**
 * Main login screen composable
 */
@Composable
fun LoginScreen(
    onLogin: (username: String, password: String, rememberMe: Boolean) -> Unit,
    onRegister: (username: String, email: String, password: String) -> Unit,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    modifier: Modifier = Modifier
) {
    var loginState by remember { mutableStateOf(LoginState()) }
    var registerState by remember { mutableStateOf(RegisterState()) }

    // Update loading state from parent
    LaunchedEffect(isLoading) {
        loginState = loginState.copy(isLoading = isLoading)
        registerState = registerState.copy(isLoading = isLoading)
    }

    // Update error message from parent
    LaunchedEffect(errorMessage) {
        loginState = loginState.copy(errorMessage = errorMessage)
        registerState = registerState.copy(errorMessage = errorMessage)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        RustscapeColors.BackgroundDark,
                        RustscapeColors.Background,
                        RustscapeColors.BackgroundDark
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(16.dp)
        ) {
            // Game title
            GameTitle()

            Spacer(modifier = Modifier.height(32.dp))

            // Login card
            LoginCard(
                loginState = loginState,
                registerState = registerState,
                onLoginStateChange = { loginState = it },
                onRegisterStateChange = { registerState = it },
                onLogin = onLogin,
                onRegister = onRegister
            )
        }
    }
}

/**
 * Game title header
 */
@Composable
private fun GameTitle() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "RUSTSCAPE",
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            color = RustscapeColors.TextGold,
            letterSpacing = 4.sp
        )
        Text(
            text = "A RuneScape-Inspired Adventure",
            fontSize = 14.sp,
            color = RustscapeColors.TextSecondary,
            letterSpacing = 2.sp
        )
    }
}

/**
 * Main login card containing tabs and forms
 */
@Composable
private fun LoginCard(
    loginState: LoginState,
    registerState: RegisterState,
    onLoginStateChange: (LoginState) -> Unit,
    onRegisterStateChange: (RegisterState) -> Unit,
    onLogin: (String, String, Boolean) -> Unit,
    onRegister: (String, String, String) -> Unit
) {
    Card(
        modifier = Modifier
            .width(400.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = 2.dp,
                color = RustscapeColors.Border,
                shape = RoundedCornerShape(8.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = RustscapeColors.PanelBackground
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Tab buttons
            TabRow(loginState.selectedTab, onLoginStateChange, loginState)

            Spacer(modifier = Modifier.height(24.dp))

            // Animated content switch between login and register
            AnimatedContent(
                targetState = loginState.selectedTab,
                transitionSpec = {
                    fadeIn() + slideInHorizontally { if (targetState == LoginTab.LOGIN) -it else it } togetherWith
                            fadeOut() + slideOutHorizontally { if (targetState == LoginTab.LOGIN) it else -it }
                },
                label = "tab_content"
            ) { tab ->
                when (tab) {
                    LoginTab.LOGIN -> LoginForm(
                        state = loginState,
                        onStateChange = onLoginStateChange,
                        onSubmit = onLogin
                    )

                    LoginTab.REGISTER -> RegisterForm(
                        state = registerState,
                        onStateChange = onRegisterStateChange,
                        onSubmit = onRegister
                    )
                }
            }
        }
    }
}

/**
 * Tab row for switching between login and register
 */
@Composable
private fun TabRow(
    selectedTab: LoginTab,
    onLoginStateChange: (LoginState) -> Unit,
    loginState: LoginState
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TabButton(
            text = "Login",
            isSelected = selectedTab == LoginTab.LOGIN,
            onClick = { onLoginStateChange(loginState.copy(selectedTab = LoginTab.LOGIN, errorMessage = null)) },
            modifier = Modifier.weight(1f)
        )
        TabButton(
            text = "Register",
            isSelected = selectedTab == LoginTab.REGISTER,
            onClick = { onLoginStateChange(loginState.copy(selectedTab = LoginTab.REGISTER, errorMessage = null)) },
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Individual tab button
 */
@Composable
private fun TabButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) RustscapeColors.Primary else RustscapeColors.SurfaceVariant,
            contentColor = if (isSelected) RustscapeColors.OnPrimary else RustscapeColors.TextSecondary
        ),
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = text,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

/**
 * Login form
 */
@Composable
private fun LoginForm(
    state: LoginState,
    onStateChange: (LoginState) -> Unit,
    onSubmit: (String, String, Boolean) -> Unit
) {
    val focusManager = LocalFocusManager.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Username field
        RustscapeTextField(
            value = state.username,
            onValueChange = { onStateChange(state.copy(username = it, errorMessage = null)) },
            label = "Username",
            enabled = !state.isLoading,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Down) }
            )
        )

        // Password field
        RustscapeTextField(
            value = state.password,
            onValueChange = { onStateChange(state.copy(password = it, errorMessage = null)) },
            label = "Password",
            isPassword = true,
            enabled = !state.isLoading,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    focusManager.clearFocus()
                    if (state.username.isNotBlank() && state.password.isNotBlank()) {
                        onSubmit(state.username, state.password, state.rememberMe)
                    }
                }
            )
        )

        // Remember me checkbox
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = state.rememberMe,
                onCheckedChange = { onStateChange(state.copy(rememberMe = it)) },
                enabled = !state.isLoading,
                colors = CheckboxDefaults.colors(
                    checkedColor = RustscapeColors.Primary,
                    uncheckedColor = RustscapeColors.TextMuted
                )
            )
            Text(
                text = "Remember me",
                color = RustscapeColors.TextSecondary,
                fontSize = 14.sp
            )
        }

        // Error message
        AnimatedVisibility(visible = state.errorMessage != null) {
            Text(
                text = state.errorMessage ?: "",
                color = RustscapeColors.Error,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Login button
        RustscapeButton(
            text = if (state.isLoading) "Logging in..." else "Login",
            onClick = { onSubmit(state.username, state.password, state.rememberMe) },
            enabled = !state.isLoading && state.username.isNotBlank() && state.password.isNotBlank(),
            isLoading = state.isLoading,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Registration form
 */
@Composable
private fun RegisterForm(
    state: RegisterState,
    onStateChange: (RegisterState) -> Unit,
    onSubmit: (String, String, String) -> Unit
) {
    val focusManager = LocalFocusManager.current

    // Password strength calculation
    val passwordStrength = calculatePasswordStrength(state.password)
    val passwordsMatch = state.password == state.confirmPassword && state.password.isNotEmpty()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Username field
        RustscapeTextField(
            value = state.username,
            onValueChange = { onStateChange(state.copy(username = it, errorMessage = null)) },
            label = "Username",
            enabled = !state.isLoading,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Down) }
            )
        )

        // Email field
        RustscapeTextField(
            value = state.email,
            onValueChange = { onStateChange(state.copy(email = it, errorMessage = null)) },
            label = "Email",
            enabled = !state.isLoading,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Down) }
            )
        )

        // Password field with strength indicator
        Column {
            RustscapeTextField(
                value = state.password,
                onValueChange = { onStateChange(state.copy(password = it, errorMessage = null)) },
                label = "Password",
                isPassword = true,
                enabled = !state.isLoading,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                )
            )

            // Password strength bar
            if (state.password.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                PasswordStrengthIndicator(strength = passwordStrength)
            }
        }

        // Confirm password field
        RustscapeTextField(
            value = state.confirmPassword,
            onValueChange = { onStateChange(state.copy(confirmPassword = it, errorMessage = null)) },
            label = "Confirm Password",
            isPassword = true,
            enabled = !state.isLoading,
            isError = state.confirmPassword.isNotEmpty() && !passwordsMatch,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    focusManager.clearFocus()
                    if (canRegister(state)) {
                        onSubmit(state.username, state.email, state.password)
                    }
                }
            )
        )

        // Terms checkbox
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = state.acceptTerms,
                onCheckedChange = { onStateChange(state.copy(acceptTerms = it)) },
                enabled = !state.isLoading,
                colors = CheckboxDefaults.colors(
                    checkedColor = RustscapeColors.Primary,
                    uncheckedColor = RustscapeColors.TextMuted
                )
            )
            Text(
                text = "I accept the Terms of Service",
                color = RustscapeColors.TextSecondary,
                fontSize = 14.sp
            )
        }

        // Error message
        AnimatedVisibility(visible = state.errorMessage != null) {
            Text(
                text = state.errorMessage ?: "",
                color = RustscapeColors.Error,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Register button
        RustscapeButton(
            text = if (state.isLoading) "Creating Account..." else "Create Account",
            onClick = { onSubmit(state.username, state.email, state.password) },
            enabled = !state.isLoading && canRegister(state),
            isLoading = state.isLoading,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Custom styled text field for Rustscape
 */
@Composable
private fun RustscapeTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    enabled: Boolean = true,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    var passwordVisible by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        isError = isError,
        singleLine = true,
        visualTransformation = if (isPassword && !passwordVisible) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        },
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = RustscapeColors.Primary,
            unfocusedBorderColor = RustscapeColors.Border,
            focusedLabelColor = RustscapeColors.Primary,
            unfocusedLabelColor = RustscapeColors.TextMuted,
            cursorColor = RustscapeColors.Primary,
            focusedTextColor = RustscapeColors.TextWhite,
            unfocusedTextColor = RustscapeColors.TextSecondary,
            errorBorderColor = RustscapeColors.Error,
            errorLabelColor = RustscapeColors.Error
        ),
        shape = RoundedCornerShape(4.dp)
    )
}

/**
 * Custom styled button for Rustscape
 */
@Composable
private fun RustscapeButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        enabled = enabled && !isLoading,
        colors = ButtonDefaults.buttonColors(
            containerColor = RustscapeColors.Primary,
            contentColor = RustscapeColors.OnPrimary,
            disabledContainerColor = RustscapeColors.SurfaceVariant,
            disabledContentColor = RustscapeColors.TextMuted
        ),
        shape = RoundedCornerShape(4.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = RustscapeColors.OnPrimary,
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
    }
}

/**
 * Password strength indicator bar
 */
@Composable
private fun PasswordStrengthIndicator(strength: PasswordStrength) {
    Column {
        LinearProgressIndicator(
            progress = { strength.progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = strength.color,
            trackColor = RustscapeColors.SurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = strength.label,
            color = strength.color,
            fontSize = 12.sp
        )
    }
}

/**
 * Password strength data class
 */
data class PasswordStrength(
    val progress: Float,
    val label: String,
    val color: Color
)

/**
 * Calculate password strength
 */
private fun calculatePasswordStrength(password: String): PasswordStrength {
    if (password.isEmpty()) {
        return PasswordStrength(0f, "", RustscapeColors.TextMuted)
    }

    var score = 0

    // Length scoring
    when {
        password.length >= 12 -> score += 3
        password.length >= 8 -> score += 2
        password.length >= 6 -> score += 1
    }

    // Character variety scoring
    if (password.any { it.isUpperCase() }) score++
    if (password.any { it.isLowerCase() }) score++
    if (password.any { it.isDigit() }) score++
    if (password.any { !it.isLetterOrDigit() }) score++

    return when {
        score >= 6 -> PasswordStrength(1f, "Strong", RustscapeColors.Success)
        score >= 4 -> PasswordStrength(0.66f, "Medium", RustscapeColors.Warning)
        score >= 2 -> PasswordStrength(0.33f, "Weak", RustscapeColors.Error)
        else -> PasswordStrength(0.15f, "Very Weak", RustscapeColors.Error)
    }
}

/**
 * Check if registration form is valid
 */
private fun canRegister(state: RegisterState): Boolean {
    return state.username.length >= 3 &&
            state.email.contains("@") &&
            state.password.length >= 6 &&
            state.password == state.confirmPassword &&
            state.acceptTerms
}
