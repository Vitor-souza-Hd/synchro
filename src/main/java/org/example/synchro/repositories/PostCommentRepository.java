package org.example.synchro.repositories;

import org.example.synchro.entities.PostComment;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PostCommentRepository extends JpaRepository<PostComment, Long> {
    @EntityGraph(attributePaths = {"author", "author.data", "parent", "parent.author", "parent.author.data"})
    List<PostComment> findByPostIdAndIdLessThanOrderByIdDesc(long postId, long before, Pageable pageable);

    @EntityGraph(attributePaths = {"author", "author.data", "parent", "parent.author", "parent.author.data"})
    Optional<PostComment> findByPostIdAndId(long postId, long id);
}
