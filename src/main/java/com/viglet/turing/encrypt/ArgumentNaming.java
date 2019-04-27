package com.viglet.turing.encrypt;

final class ArgumentNaming {

    
    static final String ARG_VERBOSE = "verbose";
    
    static final String ARG_INPUT = "input";
    
    static final String ARG_PASSWORD = "password";
    
    static final String ARG_ALGORITHM = "algorithm";
    
    static final String ARG_ITERATIONS = "iterations";
    
    static final String ARG_KEY_OBTENTION_ITERATIONS = 
        "keyObtentionIterations";
    
    static final String ARG_SALT_SIZE_BYTES = "saltSizeBytes";
    
    static final String ARG_SALT_GENERATOR_CLASS_NAME = 
        "saltGeneratorClassName";
    
    static final String ARG_PROVIDER_CLASS_NAME = "providerClassName";
    
    static final String ARG_PROVIDER_NAME = "providerName";
    
    static final String ARG_INVERT_POSITION_OF_SALT_IN_MESSAGE_BEFORE_DIGESTING =
        "invertPositionOfSaltInMessageBeforeDigesting";
    
    static final String ARG_INVERT_POSITION_OF_PLAIN_SALT_IN_ENCRYPTION_RESULTS =
        "invertPositionOfPlainSaltInEncryptionResults";

    static final String ARG_USE_LENIENT_SALT_SIZE_CHECK =
        "useLenientSaltSizeCheck";
    
    static final String ARG_UNICODE_NORMALIZATION_IGNORED = 
        "unicodeNormalizationIgnored";
    
    static final String ARG_STRING_OUTPUT_TYPE = 
        "stringOutputType";
    
    static final String ARG_PREFIX = "prefix";
    
    static final String ARG_SUFFIX = "suffix";

    
    
    // Instantiation is not allowed
    private ArgumentNaming() {
        super();
    }
    
}