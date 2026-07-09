package com.thresholdinc.luxe.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thresholdinc.luxe.data.EncryptedVault
import com.thresholdinc.luxe.ui.components.GlassCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForgotPasswordScreen(
    vault: EncryptedVault,
    onNavigateToLogin: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0C0C0E))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "RESET VAULT PASSCODE",
                color = Color(0xFFE8D9C0),
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraLight,
                letterSpacing = 3.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Provide your registered email to overwrite cryptographic keys.",
                color = Color(0xFFF5F0E6).copy(alpha = 0.5f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Light,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (successMessage != null) {
                        Text(
                            text = successMessage!!,
                            color = Color(0xFFE8D9C0),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Normal,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Button(
                            onClick = onNavigateToLogin,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFE8D9C0),
                                contentColor = Color(0xFF0C0C0E)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("GO TO SIGN IN", fontWeight = FontWeight.Normal, letterSpacing = 2.sp)
                        }
                    } else {
                        OutlinedTextField(
                            value = email,
                            onValueChange = {
                                email = it
                                errorMessage = null
                            },
                            label = { Text("Registered Email", color = Color(0xFFE8D9C0).copy(alpha = 0.6f)) },
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
                            value = newPassword,
                            onValueChange = {
                                newPassword = it
                                errorMessage = null
                            },
                            label = { Text("New Vault Password", color = Color(0xFFE8D9C0).copy(alpha = 0.6f)) },
                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                focusedBorderColor = Color(0xFFE8D9C0),
                                unfocusedBorderColor = Color(0xFFE8D9C0).copy(alpha = 0.2f),
                                focusedLabelColor = Color(0xFFE8D9C0),
                                cursorColor = Color(0xFFE8D9C0)
                            ),
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = {
                                confirmPassword = it
                                errorMessage = null
                            },
                            label = { Text("Confirm New Password", color = Color(0xFFE8D9C0).copy(alpha = 0.6f)) },
                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                focusedBorderColor = Color(0xFFE8D9C0),
                                unfocusedBorderColor = Color(0xFFE8D9C0).copy(alpha = 0.2f),
                                focusedLabelColor = Color(0xFFE8D9C0),
                                cursorColor = Color(0xFFE8D9C0)
                            ),
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth()
                        )

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
                                    email.isBlank() || newPassword.isBlank() || confirmPassword.isBlank() -> {
                                        errorMessage = "Please fill in all fields."
                                    }
                                    newPassword != confirmPassword -> {
                                        errorMessage = "Passwords do not match."
                                    }
                                    else -> {
                                        val success = vault.resetPassword(email, newPassword)
                                        if (success) {
                                            successMessage = "Vault password updated successfully."
                                        } else {
                                            errorMessage = "Email address not found in vault database."
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
                            Text("RE-ESTABLISH PASSWORD", fontWeight = FontWeight.Normal, letterSpacing = 1.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Back to Sign In",
                color = Color(0xFFE8D9C0),
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.clickable { onNavigateToLogin() }
            )
        }
    }
}
