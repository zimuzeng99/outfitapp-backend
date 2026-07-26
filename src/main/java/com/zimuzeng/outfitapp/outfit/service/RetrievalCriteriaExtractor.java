package com.zimuzeng.outfitapp.outfit.service;

import com.zimuzeng.outfitapp.outfit.model.RetrievalCriteria;

/**
 * Turns a user's free-text outfit request into structured {@link RetrievalCriteria} used to
 * narrow their wardrobe to a candidate pool. Implemented by {@link QwenRetrievalCriteriaExtractor}.
 */
public interface RetrievalCriteriaExtractor {

    RetrievalCriteria extract(String context);
}
