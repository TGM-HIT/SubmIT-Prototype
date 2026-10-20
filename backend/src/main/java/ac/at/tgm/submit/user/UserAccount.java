package ac.at.tgm.submit.user;

import ac.at.tgm.submit.security.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "user_accounts")
public class UserAccount {
    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 80)
    private String username;

    @Column(nullable = false, length = 120)
    private String displayName;

    @Column(nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_class_id")
    private StudentClassEntity studentClass;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "student_elective_subjects",
            joinColumns = @jakarta.persistence.JoinColumn(name = "student_id"),
            inverseJoinColumns = @jakarta.persistence.JoinColumn(name = "subject_id"))
    private List<ElectiveSubjectEntity> electiveSubjects = new ArrayList<>();

    protected UserAccount() {
    }

    public UserAccount(UUID id, String username, String displayName, String passwordHash, Role role) {
        this.id = id;
        this.username = username;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    public UUID getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public StudentClassEntity getStudentClass() {
        return studentClass;
    }

    public void setStudentClass(StudentClassEntity studentClass) {
        this.studentClass = studentClass;
    }

    public List<ElectiveSubjectEntity> getElectiveSubjects() {
        return electiveSubjects;
    }

    public void setElectiveSubjects(List<ElectiveSubjectEntity> electiveSubjects) {
        this.electiveSubjects = new ArrayList<>(electiveSubjects);
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }
}
