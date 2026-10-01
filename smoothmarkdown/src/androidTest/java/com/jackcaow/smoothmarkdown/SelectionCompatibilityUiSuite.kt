package com.jackcaow.smoothmarkdown

import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(Suite::class)
@Suite.SuiteClasses(
    NonTextSelectionInstrumentedTest::class,
    ReaderDetailsSelectionTest::class,
    ReaderHtmlKbdSelectionTest::class,
    ReaderNonTextSelectionTest::class,
    ReaderSelectionActionsUiTest::class,
    ReaderWholeDocumentSelectionUiTest::class,
    ReaderSelectionCompatibilityUiTest::class,
)
class SelectionCompatibilityUiSuite
