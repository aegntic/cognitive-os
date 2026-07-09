package com.thresholdinc.luxe.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thresholdinc.luxe.data.EncryptedVault
import com.thresholdinc.luxe.ui.components.GlassCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignUpScreen(
    vault: EncryptedVault,
    onNavigateToLogin: () -> Unit,
    onNavigateToPrivacyPolicy: () -> Unit,
    onNavigateToTerms: () -> Unit,
    onRegisterSuccess: (email: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var acceptTerms by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0C0C0E))
            .padding(24.dp)
            .verticalScroll(scrollState),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "CREATE ACCOUNT",
                color = Color(0xFFE8D9C0),
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraLight,
                letterSpacing = 4.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Establish your secure cryptographic gateway.",
                color = Color(0xFFF5F0E6).copy(alpha = 0.5f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Light,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            errorMessage = null
                        },
                        label = { Text("Full Name", color = Color(0xFFE8D9C0).copy(alpha = 0.6f)) },
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            focusedBorderColor = Color(0xFFE8D9C0),
                            unfocusedBorderColor = Color(0xFFE8D9C0).copy(alpha = 0.2f),
                            focusedLabelColor = Color(0xFFE8D9C0),
                            cursorColor = Color(0xFFE8D9C0)
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = email,
                        onValueChange = {
                            email = it
                            errorMessage = null
                        },
                        label = { Text("Email Address", color = Color(0xFFE8D9C0).copy(alpha = 0.6f)) },
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            focusedBorderColor = Color(0xFFE8D9C0),
                            unfocusedBorderColor = Color(0xFFE8D9C0).copy(alpha = 0.2f),
                            focusedLabelColor = Color(0xFFE8D9C0),
                            cursorColor = Color(0xFFE8D9C0)
                        ),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            errorMessage = null
                        },
                        label = { Text("Vault Password", color = Color(0xFFE8D9C0).copy(alpha = 0.6f)) },
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            focusedBorderColor = Color(0xFFE8D9C0),
                            unfocusedBorderColor = Color(0xFFE8D9C0).copy(alpha = 0.2f),
                            focusedLabelColor = Color(0xFFE8D9C0),
                            cursorColor = Color(0xFFE8D9C0)
                        ),
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(image, contentDescription = "Toggle password visibility", tint = Color(0xFFE8D9C0))
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = {
                            confirmPassword = it
                            errorMessage = null
                        },
                        label = { Text("Confirm Password", color = Color(0xFFE8D9C0).copy(alpha = 0.6f)) },
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            focusedBorderColor = Color(0xFFE8D9C0),
                            unfocusedBorderColor = Color(0xFFE8D9C0).copy(alpha = 0.2f),
                            focusedLabelColor = Color(0xFFE8D9C0),
                            cursorColor = Color(0xFFE8D9C0)
                        ),
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Terms and Privacy acceptance
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = acceptTerms,
                            onCheckedChange = { acceptTerms = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = Color(0xFFE8D9C0),
                                uncheckedColor = Color(0xFFE8D9C0).copy(alpha = 0.4f),
                                checkmarkColor = Color(0xFF0C0C0E)
                            )
                        )
                        
                        val annotatedText = buildAnnotatedString {
                            append("I accept the ")
                            pushStringAnnotation(tag = "TERMS", annotation = "terms")
                            withStyle(style = SpanStyle(color = Color(0xFFE8D9C0), fontWeight = FontWeight.Normal)) {
                                append("Terms")
                            }
                            pop()
                            append(" & ")
                            pushStringAnnotation(tag = "PRIVACY", annotation = "privacy")
                            withStyle(style = SpanStyle(color = Color(0xFFE8D9C0), fontWeight = FontWeight.Normal)) {
                                append("Privacy Policy")
                            }
                            pop()
                        }

                        ClickableText(
                            text = annotatedText,
                            style = LocalTextStyle.current.copy(
                                color = Color(0xFFF5F0E6).copy(alpha = 0.6f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Light
                            ),
                            onClick = { offset ->
                                annotatedText.getStringAnnotations(tag = "TERMS", start = offset, end = offset)
                                    .firstOrNull()?.let { onNavigateToTerms() }
                                annotatedText.getStringAnnotations(tag = "PRIVACY", start = offset, end = offset)
                                    .firstOrNull()?.let { onNavigateToPrivacyPolicy() }
                            }
                        )
                    }

                    if (errorMessage != null) {
                        Text(
                            text = errorMessage!!,
                            color = Color(0xFFCF6679),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal
                        )
                    }

                    Button(
                        onClick = {
                            when {
                                name.isBlank() || email.isBlank() || password.isBlank() || confirmPassword.isBlank() -> {
                                    errorMessage = "Please fill in all fields."
                                }
                                password != confirmPassword -> {
                                    errorMessage = "Passwords do not match."
                                }
                                !acceptTerms -> {
                                    errorMessage = "You must accept the Terms and Privacy Policy."
                                }
                                else -> {
                                    val success = vault.registerUser(email, name, password)
                                    if (success) {
                                        vault.setConfig("active_user", email)
                                        onRegisterSuccess(email)
                                    } else {
                                        errorMessage = "An account with this email already exists."
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFE8D9C0),
                            contentColor = Color(0xFF0C0C0E)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("ESTABLISH VAULT", fontWeight = FontWeight.Normal, letterSpacing = 1.5.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.clickable { onNavigateToLogin() }
            ) {
                Text(
                    text = "Already have an account? ",
                    color = Color(0xFFF5F0E6).copy(alpha = 0.5f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Light
                )
                Text(
                    text = "Sign In",
                    color = Color(0xFFE8D9C0),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal
                )
            }
        }
    }
}
