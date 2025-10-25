package com.smartcar.app.data

import com.google.firebase.firestore.FirebaseFirestore

object FirestoreHelper {
    val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    fun placeholderInit() {
        // TODO: Aquí conectaremos la sesión del usuario y guardaremos datos más adelante
    }
}
