package vn.focal.warper

import java.util.*

typealias Creator<T> = () -> T

typealias Remover<T> = (resource: T) -> Unit

class ResourceManager {

    private val onDestroyQueue = Stack<() -> Unit>()

    fun <T> addResource(
            creator: Creator<T>,
            remover: Remover<T>?
    ): T {
        val resource = creator()

        if (remover != null) {
            onDestroyQueue.push {
                remover(resource)
            }
        }

        return resource
    }

    fun destroy() {
        while (!onDestroyQueue.empty()) {
            val remover = onDestroyQueue.pop()
            remover()
        }
    }

}