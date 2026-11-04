package app.kestrel.ui.shell

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.IntOffset
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene
import app.kestrel.ui.theme.KestrelTheme

/** The three screen transitions NavDisplay takes. */
@Immutable
internal class ScreenTransitions(
    val push: AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform,
    val pop: AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform,
    val predictivePop: AnimatedContentTransitionScope<Scene<NavKey>>.(Int) -> ContentTransform,
)

/**
 * DESIGN 2.10. Push: the new screen slides in from 1/8 of the width and fades in while the old one fades out and
 * slides 1/12 the other way; pop reverses it. Predictive back scales the leaving screen from 1 to 0.92 and fades it.
 * Slides take defaultSpatialSpec, fade-ins defaultEffectsSpec and fade-outs fastEffectsSpec. With reduced motion
 * screens cut.
 */
@Composable
internal fun rememberScreenTransitions(): ScreenTransitions {
    val motion = MaterialTheme.motionScheme
    val reduceMotion = KestrelTheme.reduceMotion
    val slide = motion.defaultSpatialSpec<IntOffset>()
    val fadeInSpec = motion.defaultEffectsSpec<Float>()
    val fadeOutSpec = motion.fastEffectsSpec<Float>()
    val scaleSpec = motion.defaultEffectsSpec<Float>()
    return remember(reduceMotion, slide, fadeInSpec, fadeOutSpec, scaleSpec) {
        if (reduceMotion) {
            val cut = EnterTransition.None togetherWith ExitTransition.None
            ScreenTransitions(push = { cut }, pop = { cut }, predictivePop = { cut })
        } else {
            ScreenTransitions(
                push = {
                    (slideInHorizontally(slide) { it / 8 } + fadeIn(fadeInSpec)) togetherWith
                        (fadeOut(fadeOutSpec) + slideOutHorizontally(slide) { -it / 12 })
                },
                pop = {
                    (slideInHorizontally(slide) { -it / 12 } + fadeIn(fadeInSpec)) togetherWith
                        (fadeOut(fadeOutSpec) + slideOutHorizontally(slide) { it / 8 })
                },
                predictivePop = {
                    fadeIn(fadeInSpec) togetherWith (scaleOut(scaleSpec, targetScale = 0.92f) + fadeOut(scaleSpec))
                },
            )
        }
    }
}
