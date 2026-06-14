package com.daon.rewrite.coverletter.repository;

import com.daon.rewrite.coverletter.entity.CoverLetter;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryCoverLetterRepository implements CoverLetterRepository {

    private final Map<String, CoverLetter> store = new ConcurrentHashMap<>();

    @Override
    public CoverLetter save(CoverLetter coverLetter) {
        store.put(coverLetter.getId(), coverLetter);
        return coverLetter;
    }

    @Override
    public Optional<CoverLetter> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }
}
