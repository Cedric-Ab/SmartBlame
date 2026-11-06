import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

abstract class SmartBlameExtension @Inject constructor(
    objects: ObjectFactory
) {
    val ignoreWhitespace: Property<Boolean> =
        objects.property(Boolean::class.java).convention(false)

    val ignoreImports: Property<Boolean> =
        objects.property(Boolean::class.java).convention(false)

    val ignoreComments: Property<Boolean> =
        objects.property(Boolean::class.java).convention(false)

    val separateJavadoc: Property<Boolean> =
        objects.property(Boolean::class.java).convention(false)
}
