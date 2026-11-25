package com.dispatchflow.archfixture.zeta.gate;

import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportResource;

/** Rule 15: a configuration that wires beans from an XML resource, which its list doesn't show. */
@ImportResource("classpath:zeta-beans.xml")
@Import({})
class ResourceConfiguration {}
