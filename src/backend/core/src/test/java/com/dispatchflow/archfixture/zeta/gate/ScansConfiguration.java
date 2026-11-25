package com.dispatchflow.archfixture.zeta.gate;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScans;
import org.springframework.context.annotation.Import;

/** Rule 15: a configuration that scans through the container annotation of repeated scans. */
@ComponentScans(@ComponentScan)
@Import({})
class ScansConfiguration {}
