package com.thresholdinc.luxe.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thresholdinc.luxe.data.EncryptedVault
import com.thresholdinc.luxe.ui.components.GlassCard

@Composable
fun LogoutConfirmationScreen(
    vault: EncryptedVault,
    onConfirmLogout: () -> Unit,
    onCancel: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0C0C0E))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "LOCK VAULT",
                    color = Color(0xFFE8D9C0),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = 3.sp,
                    textAlign = TextAlign.Center
                )
                
                Text(
                    text = "Are you sure you want to end your active session?\n\nThis will lock your cryptographic database and clear active memory keys. You must re-authenticate to restore your sovereign gatekeeping metrics.",
                    color = Color(0xFFF5F0E6).copy(alpha = 0.8f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Light,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            vault.deleteConfig("active_user")
                            onConfirmLogout()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFCF6679),
                            contentColor = Color(0xFF0C0C0E)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("LOCK & LOG OUT", fontWeight = FontWeight.Normal, letterSpacing = 1.sp)
                    }

                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFFE8D9C0)
                        ),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFE8D9C0).copy(alpha = 0.3f))
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("CANCEL", fontWeight = FontWeight.Light, letterSpacing = 1.sp)
                    }
                }
            }
        }
    }
}
