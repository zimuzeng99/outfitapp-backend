package com.zimuzeng.outfitapp.buyadvice.service;

import com.zimuzeng.outfitapp.garment.model.ExtractedGarmentMetadata;
import com.zimuzeng.outfitapp.garment.model.GarmentMetadata;
import java.util.List;

public interface BuyAdvisor {

    BuyAdvisorResult advise(
            ExtractedGarmentMetadata candidate,
            String candidateLabel,
            String context,
            List<GarmentMetadata> nearDuplicates,
            List<GarmentMetadata> wardrobeCandidates,
            boolean chinese);
}
