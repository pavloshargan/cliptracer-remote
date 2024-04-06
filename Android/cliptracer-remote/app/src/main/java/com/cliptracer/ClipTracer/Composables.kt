package com.cliptracer.ClipTracer

import androidx.compose.material3.*
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.unit.Dp


@Composable
fun CustomButton(text: String, icon: ImageVector? = null, paddingH: Dp = 8.dp, paddingV: Dp = 8.dp, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFFFFA500),
            contentColor = Color.White
        ),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .padding(vertical = paddingV, horizontal = paddingH),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = text, tint = Color.White)
            Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
        }
        Text(text, color = Color.White) // Using Color.Unspecified to inherit the contentColor
    }
}
@Composable
fun TextLabel(
    text1: String,
    icon1: ImageVector,
    scale: TextUnit,
    gap: Dp = 8.dp
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentWidth(Alignment.CenterHorizontally)
    ) {
        Icon(
            icon1,
            contentDescription = null, // Descriptive text for the icon
            tint = Color(0xFFFFA500), // Icon color set to orange
        )
        Spacer(modifier = Modifier.size(gap)) // Space between icon and text
        Text(
            text = text1,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = scale),
            color = Color(0xFFFFA500), // Orange color for the text
        )

    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReusableTopAppBar(
    title: String,
    onNavigationIconClick: () -> Unit
) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onNavigationIconClick) {
                Icon(Icons.Filled.ArrowBack, "Back")
            }
        }
    )
}

@Composable
fun CustomDialog(
    showDialog: Boolean,
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit
) {
    if (showDialog) {
        Dialog(onDismissRequest = onDismissRequest) {
            // Customizing the dialog's appearance and layout
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(16.dp),
                shape = MaterialTheme.shapes.medium,
                tonalElevation = 8.dp
            ) {
                content()
            }
        }
    }
}



