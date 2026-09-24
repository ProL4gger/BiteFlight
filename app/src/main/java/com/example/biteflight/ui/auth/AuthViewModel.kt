package com.example.biteflight.ui.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.biteflight.data.model.User
import com.example.biteflight.data.repository.AuthRepository
import com.example.biteflight.utils.Resource
import com.google.firebase.auth.FirebaseUser

class AuthViewModel(
    private val repository: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _signUpState = MutableLiveData<Resource<FirebaseUser>>()
    val signUpState: LiveData<Resource<FirebaseUser>> = _signUpState

    private val _signInState = MutableLiveData<Resource<FirebaseUser>>()
    val signInState: LiveData<Resource<FirebaseUser>> = _signInState

    private val _userProfileState = MutableLiveData<Resource<User>>()
    val userProfileState: LiveData<Resource<User>> = _userProfileState

    val currentUser: FirebaseUser? get() = repository.currentUser

    fun signUp(user: User, password: String) {
        repository.signUp(user, password) { result ->
            _signUpState.postValue(result)
        }
    }

    fun signIn(email: String, password: String) {
        repository.signIn(email, password) { result ->
            _signInState.postValue(result)
        }
    }

    fun fetchProfile(uid: String) {
        repository.fetchUserProfile(uid) { result ->
            _userProfileState.postValue(result)
        }
    }

    fun logout() {
        repository.signOut()
    }
}
