package fr.guim.cinedoury.core.ui

import fr.guim.cinedoury.core.data.SessionManager
import fr.guim.cinedoury.core.designsystem.image.ImageSource
import fr.guim.cinedoury.core.model.ImageRef
import fr.guim.cinedoury.core.model.User

/** [image] on the server in use, at the size it is displayed. */
fun SessionManager.imageSource(
    image: ImageRef,
): ImageSource = ImageSource { width, height -> imageUrl(image, width, height) }

/** The picture of [user] on the server in use (Jellyfin serves it at one size). */
fun SessionManager.avatarSource(user: User): ImageSource? =
    userImageUrl(user)?.let { url -> ImageSource { _, _ -> url } }
