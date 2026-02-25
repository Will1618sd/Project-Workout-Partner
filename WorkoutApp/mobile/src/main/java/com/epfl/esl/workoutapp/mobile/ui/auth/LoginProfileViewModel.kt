package com.epfl.esl.workoutapp.mobile.ui.auth

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import coil.Coil
import coil.request.ImageRequest
import coil.size.Size
import com.google.android.gms.tasks.Task
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.PutDataRequest
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import java.io.ByteArrayOutputStream
import com.epfl.esl.workoutapp.R
import com.epfl.esl.workoutapp.mobile.service.WearPaths
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth

class LoginProfileViewModel : ViewModel() {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    var storageRef = FirebaseStorage.getInstance().getReference()

    val database: FirebaseDatabase = FirebaseDatabase.getInstance()
    val profileRef: DatabaseReference = database.getReference("Profiles")
    private var _email = MutableLiveData<String>("")
    private var _username = MutableLiveData<String>("")
    private var _password = MutableLiveData<String>("")
    private var _imageUri = MutableLiveData<Uri?>(null)
    private val _userImageLoadingFinished = MutableLiveData<Boolean?>()
    private var downloadedImageDrawable: Drawable? = null
    var key: String = ""
    private val _uploadSuccess = MutableLiveData<Boolean?>()
    private val _authError = MutableLiveData<String?>()
    private val _isLoading = MutableLiveData(false)
    private val _profilePresent = MutableLiveData<Boolean?>()

    // Getter
    val uploadSuccess: LiveData<Boolean?> get() = _uploadSuccess
    val authError: LiveData<String?> get() = _authError
    val isLoading: LiveData<Boolean> get() = _isLoading
    val profilePresent: LiveData<Boolean?> get() = _profilePresent
    val email: LiveData<String> get() = _email
    val password: LiveData<String> get() = _password
    val imageUri: LiveData<Uri?> get() = _imageUri
    val userImageLoadingFinished: LiveData<Boolean?>
        get() = _userImageLoadingFinished

    private var _confirmPassword = MutableLiveData<String>("")
    val confirmPassword: LiveData<String> get() = _confirmPassword

    fun updateConfirmPassword(confirm: String) {
        _confirmPassword.value = confirm
    }

    private fun isEmailValid(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    private fun isPasswordValid(password: String): Boolean {
        return password.length >= 6
    }

    fun updateEmail(email: String) {
        _email.value = email
    }

    fun updatePassword(password: String) {
        _password.value = password
    }

    fun updateImageUri(imageUri: Uri?) {
        _imageUri.value = imageUri
    }

    fun signUp(context: Context?, dataClient: DataClient) {
        val emailInput = _email.value ?: ""
        val passInput = _password.value ?: ""
        val confirmInput = _confirmPassword.value ?: ""

        if (emailInput.isBlank()) {
            _authError.value = "Email cannot be empty."
            return
        }
        if (!isEmailValid(emailInput)) {
            _authError.value = "Enter a valid email address."
            return
        }
        if (!isPasswordValid(passInput)) {
            _authError.value = "Password must be at least 6 characters."
            return
        }
        if (passInput != confirmInput) {
            _authError.value = "Passwords do not match."
            return
        }

        _isLoading.value = true
        auth.createUserWithEmailAndPassword(emailInput, passInput)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    key = auth.currentUser?.uid ?: ""
                    if (key.isNotEmpty()) {
                        _username.value = emailInput.substringBefore("@")
                        sendDataToFireBase(context)
                        sendDataToWear(context, dataClient)
                        _profilePresent.value = true
                    }
                } else {
                    _isLoading.value = false
                    _authError.value = task.exception?.message
                }
            }
    }


    fun login(
        context: Context?,
        dataClient: DataClient
    ) {
        val emailInput = _email.value ?: ""
        val passInput = _password.value ?: ""

        if (emailInput.isEmpty() || passInput.isEmpty()) {
            _authError.value = "Please enter email and password."
            return
        }

        _isLoading.value = true
        auth.signInWithEmailAndPassword(emailInput, passInput)
            .addOnCompleteListener { task ->
                _isLoading.value = false
                if (task.isSuccessful) {
                    key = auth.currentUser?.uid ?: ""
                    profileRef.child(key).child("username").get()
                        .addOnSuccessListener { snapshot ->
                            // 4. Update the state
                            val dbUsername = snapshot.getValue(String::class.java) ?: "Unknown"
                            _username.value = dbUsername
                            Log.w("LoginProfileViewModel", "Username from db: $dbUsername")

                            // 5. Proceed with success logic
                            _authError.value = null
                            _profilePresent.value = true
                            sendDataToWear(context, dataClient, isLogin = false)
                            _isLoading.value = false
                        }
                        .addOnFailureListener {
                            // Handle database read error if needed, or just proceed
                            _username.value = "Unknown"
                            _authError.value = null
                            _profilePresent.value = true
                            _isLoading.value = false
                        }
                } else {
                    _authError.value = task.exception?.message
                    _profilePresent.value = false
                }
            }
    }



    fun sendDataToWear(context: Context?, dataClient: DataClient, isLogin: Boolean = false) {
        Log.w("LoginProfileViewModel", "Sending data to wear ${_username.value}")
        val matrix = Matrix()
        var ratio: Float?
        var imageBitmap: Bitmap?
        if (!isLogin) {
            imageBitmap = if (_imageUri.value != null) {
                MediaStore.Images.Media.getBitmap(
                    context?.contentResolver,
                    _imageUri.value
                )
            } else {
                BitmapFactory.decodeResource(context?.resources, R.drawable.user_image)
            }
//            imageBitmap = MediaStore.Images.Media
//                .getBitmap(context?.contentResolver, _imageUri.value)
            ratio = 13F
        } else {
            imageBitmap = downloadedImageDrawable?.toBitmap()
            ratio = 1F
        }
        if (imageBitmap == null) {
            return
        }

        val imageBitmapScaled = Bitmap.createScaledBitmap(
            imageBitmap,
            (imageBitmap.width / ratio).toInt(),
            (imageBitmap.height / ratio).toInt(),
            false
        )

        imageBitmap = Bitmap.createBitmap(
            imageBitmapScaled, 0, 0,
            (imageBitmap.width / ratio).toInt(),
            (imageBitmap.height / ratio).toInt(), matrix, true
        )

        val stream = ByteArrayOutputStream()
        imageBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        val imageByteArray = stream.toByteArray()

        val request: PutDataRequest = PutDataMapRequest.create(WearPaths.USER_INFO).run {
            dataMap.putLong("timestamp", System.currentTimeMillis())
            dataMap.putByteArray("profileImage", imageByteArray)
            dataMap.putString("username", _username.value ?: "")
            Log.w("LoginProfileViewModel", "Put data request: ${_username.value}")
            asPutDataRequest()
        }

        request.setUrgent()
        val putTask: Task<DataItem> = dataClient.putDataItem(request)
    }

    fun sendDataToFireBase(context: Context?) {
        profileRef.child(key).child("email").setValue(_email.value)
        profileRef.child(key).child("username").setValue(_username.value)
        profileRef.child(key).child("weight").setValue(0)
        profileRef.child(key).child("height").setValue(0)

        val profileImageRef = storageRef.child("ProfileImages/" + _email.value + ".jpg")
        /** format the image **/

        val matrix = Matrix()
        var imageBitmap: Bitmap = if (_imageUri.value != null) {
            MediaStore.Images.Media.getBitmap(
                context?.contentResolver,
                _imageUri.value
            )
        } else {
            BitmapFactory.decodeResource(context?.resources, R.drawable.user_image)
        }

        val ratio: Float = 13F
        val imageBitmapScaled = Bitmap.createScaledBitmap(
            imageBitmap, (imageBitmap.width / ratio).toInt(),
            (imageBitmap.height / ratio).toInt(), false
        )
        imageBitmap = Bitmap.createBitmap(
            imageBitmapScaled, 0, 0, (imageBitmap.width / ratio).toInt(),
            (imageBitmap.height / ratio).toInt(), matrix, true
        )
        val stream = ByteArrayOutputStream()
        imageBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        val imageByteArray = stream.toByteArray()

        /** upload it to Firebase storage **/
        val uploadProfileImage = profileImageRef.putBytes(imageByteArray)

        uploadProfileImage.addOnFailureListener {
            _uploadSuccess.value = false
        }.addOnSuccessListener { taskSnapshot ->
            profileImageRef.downloadUrl.addOnSuccessListener { uri ->
                profileRef.child(key).child("photo URL").setValue(uri.toString())
                _uploadSuccess.value = true
            }
        }
    }

    /** mobile -> LoginProfileViewModel.kt **/
    fun fetchProfile() {
        profileRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                for (user in dataSnapshot.children) {
                    val usernameDatabase = user.child("username").value?.toString()
//                        .getValue(String::class.java)
                    if (usernameDatabase != null &&
                        _username.value == usernameDatabase
                    ) {
                        key = user.key.toString()
                        _profilePresent.value = true
                        break
                    }
                }
                if (_profilePresent.value != true) {
                    _profilePresent.value = false
                }
            }

            override fun onCancelled(databaseError: DatabaseError) {}
        })
    }

    fun resetProfilePresent() {
        _profilePresent.value = null
    }

    fun loadUserImageUri(context: Context) {
        storageRef.child("ProfileImages/" + _email.value + ".jpg")
            .downloadUrl.addOnSuccessListener { uri ->
                _imageUri.value = uri
                val imageLoader = Coil.imageLoader(context)
                val request = ImageRequest.Builder(context)
                    .data(uri)
                    .size(Size.ORIGINAL)
                    .target {
                        downloadedImageDrawable = it
                        _userImageLoadingFinished.value = true
                    }
                    .build()
                imageLoader.enqueue(request)
            }.addOnFailureListener {
                _userImageLoadingFinished.value = true
            }
    }

}
