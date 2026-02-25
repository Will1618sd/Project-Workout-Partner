package com.epfl.esl.workoutapp.mobile.ui.auth

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.epfl.esl.workoutapp.mobile.ui.theme.WorkoutAppTheme
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.Wearable
import com.epfl.esl.workoutapp.R

enum class AuthMode { LOGIN, SIGN_UP }

@Composable
fun LoginProfileScreen(
    onNavigateToHome: ((LoginInfo) -> Unit),
    dataClient: DataClient,
    modifier: Modifier = Modifier,
    loginProfileViewModel: LoginProfileViewModel = viewModel()
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        val email by loginProfileViewModel.email.observeAsState(initial = "")
        val password by loginProfileViewModel.password.observeAsState(initial = "")
        val imageUri by loginProfileViewModel.imageUri.observeAsState(initial = null)

        val isLoading by loginProfileViewModel.isLoading.observeAsState(initial = false)
        val authError by loginProfileViewModel.authError.observeAsState()
        val profilePresent by loginProfileViewModel.profilePresent.observeAsState()
        val userImageLoadingFinished by loginProfileViewModel.userImageLoadingFinished.observeAsState()
        val confirmPassword by loginProfileViewModel.confirmPassword.observeAsState(initial = "")


        val context = LocalContext.current

        val resultLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult(),
            onResult = { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    val uri = result.data?.data
                    loginProfileViewModel.updateImageUri(uri)
                }
            }
        )

        LoginProfileContent(
            imageUri = imageUri,
            email = email,
            password = password,
            confirmPassword = confirmPassword,
            onEmailChanged = { loginProfileViewModel.updateEmail(it) },
            onPasswordChanged = { loginProfileViewModel.updatePassword(it) },
            onConfirmPasswordChanged = { loginProfileViewModel.updateConfirmPassword(it) },
            onLogInButtonClicked = {
//                isLoading = true
//                loginProfileViewModel.fetchProfile()
                loginProfileViewModel.login(context, dataClient)
            },
            onSignUpButtonClicked = {
//                loginProfileViewModel.sendDataToWear(context, dataClient)
//                loginProfileViewModel.sendDataToFireBase(context)
//
//                val userData = LoginInfo(
//                    loginProfileViewModel.username.value ?: "",
//                    loginProfileViewModel.imageUri.value,
//                    loginProfileViewModel.key
//                )
//                onNavigateToHome(userData)
                loginProfileViewModel.signUp(context, dataClient)
            },
            onPickImageButtonClicked = {
                val intent = Intent(Intent.ACTION_GET_CONTENT)
                intent.type = "image/*"
                resultLauncher.launch(intent)
            },
            modifier = modifier
        )

        LaunchedEffect(authError) {
            authError?.let {
                Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            }
        }

        LaunchedEffect(profilePresent) {
            if (profilePresent == true) {
//                loginProfileViewModel.loadUserImageUri(context)
                val loginInfo = LoginInfo(
                    email = loginProfileViewModel.email.value ?: "",
                    imageUri = loginProfileViewModel.imageUri.value,
                    userKey = loginProfileViewModel.key
                )
                onNavigateToHome(loginInfo)
                loginProfileViewModel.resetProfilePresent()
                loginProfileViewModel.sendDataToWear(context, dataClient, true)
            }
        }

        LaunchedEffect(userImageLoadingFinished) {
            if (userImageLoadingFinished == true) {
                val loginInfo = LoginInfo(
                    email = loginProfileViewModel.email.value ?: "",
                    imageUri = loginProfileViewModel.imageUri.value,
                    userKey = loginProfileViewModel.key
                )
                loginProfileViewModel.sendDataToWear(context, dataClient, true)
                onNavigateToHome(loginInfo)
            }
        }

        if (isLoading) {
            Column(modifier = Modifier
                .fillMaxSize()
                .background(
                    MaterialTheme.colorScheme.background
                        .copy(alpha = 0.5f)
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .width(64.dp),
                    color = MaterialTheme.colorScheme.secondary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
        }

    }
}

@Composable
fun LoginProfileContent(
    imageUri: Uri?,
    email: String,
    password: String,
    confirmPassword: String,
    onEmailChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onConfirmPasswordChanged: (String) -> Unit,
    onLogInButtonClicked: () -> Unit,
    onSignUpButtonClicked: () -> Unit,
    onPickImageButtonClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var selectedMode by remember { mutableStateOf<AuthMode?>(null) }
    var attemptedLogin by remember { mutableStateOf(false) }
    var attemptedSignUp by remember { mutableStateOf(false) }


    val emailValid = remember(email) {
        android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }
    val passwordValid = remember(password) { password.length >= 6 }
    val passwordsMatch = remember(password, confirmPassword) { password == confirmPassword }

    val showEmailError = when (selectedMode) {
        AuthMode.SIGN_UP -> attemptedSignUp && !emailValid
        AuthMode.LOGIN -> attemptedLogin && email.isBlank()
        null -> false
    }

    val showPasswordError = when (selectedMode) {
        AuthMode.SIGN_UP -> attemptedSignUp && !passwordValid
        AuthMode.LOGIN -> attemptedLogin && password.isBlank()
        null -> false
    }

    val showConfirmError =
        selectedMode == AuthMode.SIGN_UP && attemptedSignUp && !passwordsMatch

    val canSignUp = emailValid && passwordValid && passwordsMatch
    val canLogin = email.isNotBlank() && password.isNotBlank()





    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(id = R.drawable.login_img),
            contentDescription = stringResource(R.string.app_name),
            modifier = modifier
                .fillMaxWidth(0.6F)
                .height(400.dp)
        )
//        if (imageUri == null) {
//            Image(
//                painter = painterResource(id = R.drawable.pick_image),
//                contentDescription = stringResource(R.string.default_user_image),
//                modifier = modifier
//                    .fillMaxWidth()
//                    .height(400.dp)
//                    .clickable {
//                        onPickImageButtonClicked()
//                    }
//            )
//        } else {
//            AsyncImage(
//                model = imageUri,
//                contentDescription = stringResource(R.string.picked_user_image),
//                modifier = modifier
//                    .fillMaxWidth()
//                    .height(400.dp)
//                    .clickable {
//                        onPickImageButtonClicked()
//                    }
//            )
//        }
        if (selectedMode == null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp, bottom = 16.dp)
            ) {
                Text(
                    text = "Let's get moving!",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Already have an account?",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = modifier.padding(top = 4.dp)
                )
            }
            Row(
                modifier = modifier
                    .fillMaxWidth(0.9F)
                    .padding(top = 15.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        selectedMode = AuthMode.LOGIN
                        attemptedLogin = false
                        attemptedSignUp = false
                        onConfirmPasswordChanged("") // reset any stale confirm state
                    },
                    modifier = modifier.size(width = 140.dp, height = 50.dp)
                ) {
                    Text("Log in")
                }

                OutlinedButton(
                    onClick = {
                        selectedMode = AuthMode.SIGN_UP
                        attemptedLogin = false
                        attemptedSignUp = false
                        onConfirmPasswordChanged("")
                    },
                    modifier = modifier.size(width = 140.dp, height = 50.dp)
                ) {
                    Text("Sign up")
                }
            }

            return
        }

        TextField(
            value = email,
            onValueChange = onEmailChanged,
            label = { Text(stringResource(R.string.email_hint)) },
            isError = showEmailError,
            supportingText = {
                if (showEmailError) {
                    Text(
                        if (selectedMode == AuthMode.SIGN_UP) "Enter a valid email address"
                        else "Email is required"
                    )
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            textStyle = TextStyle(fontSize = 24.sp, textAlign = TextAlign.Center),
            singleLine = true,
            modifier = modifier
                .fillMaxWidth(0.9F)
                .padding(bottom = 8.dp)
        )
        TextField(
            value = password,
            onValueChange = onPasswordChanged,
            label = { Text(stringResource(R.string.password_hint)) },
            isError = showPasswordError,
            supportingText = {
                if (showPasswordError) {
                    Text(
                        if (selectedMode == AuthMode.SIGN_UP) "Password must be at least 6 characters"
                        else "Password is required"
                    )
                }
            },

            textStyle = TextStyle(fontSize = 24.sp, textAlign = TextAlign.Center),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = modifier.fillMaxWidth(0.9F)
        )
        if (selectedMode == AuthMode.SIGN_UP) {
            TextField(
                value = confirmPassword,
                onValueChange = onConfirmPasswordChanged,
                label = { Text("Confirm password") },
                isError = showConfirmError,
                supportingText = {
                    if (showConfirmError) Text("Passwords must match")
                },
                textStyle = TextStyle(fontSize = 24.sp, textAlign = TextAlign.Center),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = modifier
                    .fillMaxWidth(0.9F)
                    .padding(top = 8.dp)
            )
        }

        Row(
            modifier = modifier
                .fillMaxWidth(0.9F)
                .padding(top = 15.dp)
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Optional: allow changing mode after selection
            OutlinedButton(
                onClick = {
                    selectedMode = null
                    attemptedLogin = false
                    attemptedSignUp = false
                    onConfirmPasswordChanged("")
                },
                modifier = modifier
                    .size(width = 100.dp, height = 50.dp)
                    .padding(top = 8.dp)
            ) {
                Text("Back")
            }

            if (selectedMode == AuthMode.LOGIN) {
                Button(
                    onClick = {
                        attemptedLogin = true
                        if (canLogin) onLogInButtonClicked()
                    },
                    modifier = modifier
                        .size(width = 120.dp, height = 50.dp)
                        .padding(top = 8.dp)
                ) {
                    Text(text = stringResource(R.string.sign_in_button_text))
                }
            } else {
                OutlinedButton(
                    onClick = {
                        attemptedSignUp = true
                        if (canSignUp) onSignUpButtonClicked()
                    },
                    enabled = true, // ALWAYS clickable; errors appear after click
                    modifier = modifier
                        .size(width = 120.dp, height = 50.dp)
                        .padding(top = 8.dp)
                ) {
                    Text(text = stringResource(R.string.sign_up_button_text))
                }
            }
        }

    }
}


