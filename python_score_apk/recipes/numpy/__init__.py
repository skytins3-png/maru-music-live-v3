"""Override p4a's numpy recipe with a minimal Android libc++ header fix.

Upstream numpy 2.3.0 unique.cpp uses std::unordered_map but omitted its
direct <unordered_map> include, which NDK libc++ correctly requires.
"""
from pythonforandroid.recipes.numpy import NumpyRecipe

class AndroidNumpyRecipe(NumpyRecipe):
    patches = list(getattr(NumpyRecipe, "patches", [])) + ["unordered_map_include.patch"]

recipe = AndroidNumpyRecipe()
