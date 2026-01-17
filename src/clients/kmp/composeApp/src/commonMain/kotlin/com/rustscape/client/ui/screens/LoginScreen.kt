package com.rustscape.client.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rustscape.client.ui.components.RSColors
import com.rustscape.client.ui.components.RSScrollPanel
import com.rustscape.client.ui.components.RSStoneButton
import com.rustscape.client.ui.components.RSText
import com.rustscape.client.ui.components.TorchFlame
import com.rustscape.client.ui.components.RSSoundStoneButton
import com.rustscape.client.ui.components.RSSoundCheckbox
import com.rustscape.client.ui.components.RSErrorMessage
import com.rustscape.client.ui.components.RSSound
import com.rustscape.client.ui.components.LocalSoundManager
import kotlin.random.Random

/**
 * Login state data class
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
 * Tab selection for login screen
 */
enum class LoginTab {
    LOGIN,
    REGISTER
}

/**
 * Register state data class
 */
data class RegisterState(
    val username: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

/**
 * Classic 2008-era RuneScape Login Screen
 * Features scroll panel design with stone buttons
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
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Atmospheric background
        LoginBackground()

        // Left torch
        TorchFlame(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 60.dp)
                .offset(y = (-50).dp),
            flameWidth = 50.dp,
            flameHeight = 100.dp,
            intensity = 0.9f
        )

        // Right torch
        TorchFlame(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 60.dp)
                .offset(y = (-50).dp),
            flameWidth = 50.dp,
            flameHeight = 100.dp,
            intensity = 0.9f
        )

        // Main content
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(16.dp)
        ) {
            // Game logo/title
            GameLogo()

            Spacer(modifier = Modifier.height(24.dp))

            // Scroll panel with login form
            RSScrollPanel(
                modifier = Modifier.width(320.dp)
            ) {
                // Welcome text
                RSText(
                    text = "Welcome to Rustscape",
                    color = RSColors.TextBrown,
                    fontSize = 18,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Tab selector
                LoginTabSelector(
                    selectedTab = loginState.selectedTab,
                    onTabSelected = { tab ->
                        loginState = loginState.copy(selectedTab = tab, errorMessage = null)
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Animated content switch
                AnimatedContent(
                    targetState = loginState.selectedTab,
                    transitionSpec = {
                        fadeIn() togetherWith fadeOut()
                    },
                    label = "login_tab_content"
                ) { tab ->
                    when (tab) {
                        LoginTab.LOGIN -> LoginForm(
                            state = loginState,
                            onStateChange = { loginState = it },
                            onSubmit = onLogin
                        )

                        LoginTab.REGISTER -> RegisterForm(
                            state = registerState,
                            onStateChange = { registerState = it },
                            onSubmit = onRegister
                        )
                    }
                }

                // Error message display
                loginState.errorMessage?.let { error ->
                    Spacer(modifier = Modifier.height(12.dp))
                    RSText(
                        text = error,
                        color = RSColors.TextRed,
                        fontSize = 12,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Loading indicator
                if (loginState.isLoading) {
                    Spacer(modifier = Modifier.height(12.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = RSColors.GoldMid,
                        strokeWidth = 2.dp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // World selector (decorative for now)
            WorldSelector()
        }
    }
}

/**
 * Atmospheric dark background with torch effects
 */
@Composable
private fun LoginBackground() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        // Dark gradient background
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF1A0A1A),
                    Color(0xFF0D0D1A),
                    Color(0xFF050510),
                    Color(0xFF0D0D1A),
                    Color(0xFF1A0A1A)
                )
            )
        )

        // Add some atmospheric particles/stars
        val random = Random(42)
        for (i in 0 until 100) {
            val x = random.nextFloat() * size.width
            val y = random.nextFloat() * size.height
            val alpha = random.nextFloat() * 0.5f + 0.1f
            val starSize = random.nextFloat() * 2f + 0.5f
            drawCircle(
                color = Color.White.copy(alpha = alpha),
                radius = starSize,
                center = Offset(x, y)
            )
        }

        // Vignette effect
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.7f)
                ),
                center = Offset(size.width / 2, size.height / 2),
                radius = maxOf(size.width, size.height) * 0.7f
            )
        )
    }
}

/**
 * Classic RuneScape-style game logo
 */
@Composable
private fun GameLogo() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Main title with stone letter effect
        Box {
            // Shadow
            Text(
                text = "RUSTSCAPE",
                fontSize = 52.sp,
                fontWeight = FontWeight.Black,
                color = Color.Black,
                letterSpacing = 4.sp,
                modifier = Modifier.offset(3.dp, 3.dp)
            )
            // Stone gradient text
            Text(
                text = "RUSTSCAPE",
                fontSize = 52.sp,
                fontWeight = FontWeight.Black,
                style = LocalTextStyle.current.copy(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFD4D4D4),
                            Color(0xFF9A9A9A),
                            Color(0xFF6A6A6A),
                            Color(0xFF9A9A9A)
                        )
                    )
                ),
                letterSpacing = 4.sp
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Subtitle
        RSText(
            text = "A RuneScape-Inspired Adventure",
            color = RSColors.GoldMid,
            fontSize = 14,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Tab selector for login/register
 */
@Composable
private fun LoginTabSelector(
    selectedTab: LoginTab,
    onTabSelected: (LoginTab) -> Unit
) {
    val soundManager = LocalSoundManager.current

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RSSoundStoneButton(
            text = "Login",
            onClick = { onTabSelected(LoginTab.LOGIN) },
            modifier = Modifier.weight(1f),
            enabled = selectedTab != LoginTab.LOGIN,
            width = 130.dp,
            height = 32.dp,
            clickSound = RSSound.TAB_SWITCH
        )
        RSSoundStoneButton(
            text = "Register",
            onClick = { onTabSelected(LoginTab.REGISTER) },
            modifier = Modifier.weight(1f),
            enabled = selectedTab != LoginTab.REGISTER,
            width = 130.dp,
            height = 32.dp,
            clickSound = RSSound.TAB_SWITCH
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
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // Username field
        RSTextField(
            value = state.username,
            onValueChange = { onStateChange(state.copy(username = it)) },
            label = "Username",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Down) }
            )
        )

        // Password field
        RSTextField(
            value = state.password,
            onValueChange = { onStateChange(state.copy(password = it)) },
            label = "Password",
            isPassword = true,
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

        // Remember me checkbox with sound
        RSSoundCheckbox(
            checked = state.rememberMe,
            onCheckedChange = { onStateChange(state.copy(rememberMe = it)) },
            label = "Remember me",
            modifier = Modifier.padding(4.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Login button with sound
        RSSoundStoneButton(
            text = if (state.isLoading) "Logging in..." else "Log In",
            onClick = {
                if (state.username.isNotBlank() && state.password.isNotBlank()) {
                    onSubmit(state.username, state.password, state.rememberMe)
                }
            },
            enabled = !state.isLoading && state.username.isNotBlank() && state.password.isNotBlank(),
            width = 180.dp,
            height = 40.dp,
            clickSound = RSSound.LOGIN_CLICK
        )
    }
}

/**
 * Register form
 */
@Composable
private fun RegisterForm(
    state: RegisterState,
    onStateChange: (RegisterState) -> Unit,
    onSubmit: (String, String, String) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val passwordsMatch = state.password == state.confirmPassword && state.password.isNotBlank()
    val canRegister = state.username.isNotBlank() &&
            state.email.isNotBlank() &&
            state.password.length >= 6 &&
            passwordsMatch

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // Username field
        RSTextField(
            value = state.username,
            onValueChange = { onStateChange(state.copy(username = it)) },
            label = "Username",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Down) }
            )
        )

        // Email field
        RSTextField(
            value = state.email,
            onValueChange = { onStateChange(state.copy(email = it)) },
            label = "Email",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Down) }
            )
        )

        // Password field
        RSTextField(
            value = state.password,
            onValueChange = { onStateChange(state.copy(password = it)) },
            label = "Password (min 6 chars)",
            isPassword = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Down) }
            )
        )

        // Confirm password field
        RSTextField(
            value = state.confirmPassword,
            onValueChange = { onStateChange(state.copy(confirmPassword = it)) },
            label = "Confirm Password",
            isPassword = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    focusManager.clearFocus()
                    if (canRegister) {
                        onSubmit(state.username, state.email, state.password)
                    }
                }
            )
        )

        // Password match indicator
        if (state.confirmPassword.isNotBlank()) {
            RSText(
                text = if (passwordsMatch) "✓ Passwords match" else "✗ Passwords do not match",
                color = if (passwordsMatch) RSColors.TextGreen else RSColors.TextRed,
                fontSize = 11
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Register button with sound
        RSSoundStoneButton(
            text = if (state.isLoading) "Creating..." else "Create Account",
            onClick = {
                if (canRegister) {
                    onSubmit(state.username, state.email, state.password)
                }
            },
            enabled = !state.isLoading && canRegister,
            width = 180.dp,
            height = 40.dp,
            clickSound = RSSound.LOGIN_CLICK
        )
    }
}

/**
 * Classic RS-style text field
 */
@Composable
private fun RSTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    Column(modifier = modifier.fillMaxWidth()) {
        RSText(
            text = label,
            color = RSColors.TextBrown,
            fontSize = 12,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            singleLine = true,
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = RSColors.TextBrown,
                unfocusedTextColor = RSColors.TextBrown,
                cursorColor = RSColors.GoldMid,
                focusedBorderColor = RSColors.GoldMid,
                unfocusedBorderColor = RSColors.ScrollBorder,
                focusedContainerColor = RSColors.ScrollLight.copy(alpha = 0.3f),
                unfocusedContainerColor = RSColors.ScrollLight.copy(alpha = 0.2f)
            ),
            shape = RoundedCornerShape(4.dp),
            textStyle = LocalTextStyle.current.copy(fontSize = 14.sp)
        )
    }
}

/**
 * World selector display (decorative)
 */
@Composable
private fun WorldSelector() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .background(
                color = Color.Black.copy(alpha = 0.6f),
                shape = RoundedCornerShape(4.dp)
            )
            .border(
                width = 1.dp,
                color = RSColors.GoldDark,
                shape = RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        RSText(
            text = "World 1",
            color = RSColors.TextYellow,
            fontSize = 14,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.width(16.dp))
        RSText(
            text = "●",
            color = RSColors.TextGreen,
            fontSize = 10
        )
        Spacer(modifier = Modifier.width(4.dp))
        RSText(
            text = "Online",
            color = RSColors.TextGreen,
            fontSize = 12
        )
    }
}
