package cc.hafa.latr.data

import com.google.firebase.firestore.FirebaseFirestore

/** Null when Firebase isn't configured. Fetched per use, since a terminated instance stays dead and the next call makes a fresh one. */
internal fun firestoreOrNull(): FirebaseFirestore? = try {
    FirebaseFirestore.getInstance()
} catch (_: Exception) {
    null
}
