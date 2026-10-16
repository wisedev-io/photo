package androidx.camera.core.resolutionselector

import android.util.Size

class AspectRatioStrategy private constructor() {
    companion object { @JvmField val RATIO_4_3_FALLBACK_AUTO_STRATEGY = AspectRatioStrategy() }
}

class ResolutionStrategy(boundSize: Size, fallbackRule: Int) {
    companion object {
        const val FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER = 3
        const val FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER = 1
    }
}

class ResolutionSelector private constructor() {
    class Builder {
        fun setAspectRatioStrategy(strategy: AspectRatioStrategy): Builder = this
        fun setResolutionStrategy(strategy: ResolutionStrategy): Builder = this
        fun build(): ResolutionSelector = ResolutionSelector()
    }
}
