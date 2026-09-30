package com.jackcaow.smoothmarkdown
import org.junit.runner.RunWith
import org.junit.runners.Suite
@RunWith(Suite::class)
@Suite.SuiteClasses(
    MarkdownDesignTokensUiTest::class,
    MarkdownRegistryUpdatesUiTest::class,
    NativeImageLoaderTest::class,
    ImageBuilderRenderTest::class,
)
class PublicLibraryUiSuite
