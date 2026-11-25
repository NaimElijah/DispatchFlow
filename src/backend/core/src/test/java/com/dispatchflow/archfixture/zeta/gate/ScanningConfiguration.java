package com.dispatchflow.archfixture.zeta.gate;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

/** Rule 15: a configuration that wires beans by a scan, which its list doesn't show. */
@ComponentScan
@Import({})
class ScanningConfiguration {}
