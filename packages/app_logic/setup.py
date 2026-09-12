from setuptools import setup
from Cython.Build import cythonize

setup(
    name="app_logic",
    version="1.0",
    ext_modules=cythonize("app_logic.pyx", language_level=3),
)
