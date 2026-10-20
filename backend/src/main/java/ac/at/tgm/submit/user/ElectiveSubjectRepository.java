package ac.at.tgm.submit.user;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ElectiveSubjectRepository extends JpaRepository<ElectiveSubjectEntity, UUID> {
}
