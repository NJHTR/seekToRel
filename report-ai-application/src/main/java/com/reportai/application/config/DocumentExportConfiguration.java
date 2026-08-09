package com.reportai.application.config;

import com.reportai.review.CompletenessReviewEngine;
import com.reportai.review.ReviewEngine;
import com.reportai.word.DefaultWordReleaseService;
import com.reportai.word.PoiWordBuilder;
import com.reportai.word.TemplatePreservingWordBuilder;
import com.reportai.word.WordBuilder;
import com.reportai.word.WordReleaseService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class DocumentExportConfiguration {
    @Bean
    public ReviewEngine completenessReviewEngine() {
        return new CompletenessReviewEngine();
    }

    @Bean
    public WordBuilder poiWordBuilder() {
        return new TemplatePreservingWordBuilder(new PoiWordBuilder());
    }

    @Bean
    public WordReleaseService wordReleaseService(ReviewEngine reviewEngine, WordBuilder wordBuilder) {
        return new DefaultWordReleaseService(reviewEngine, wordBuilder);
    }
}
