package mops.gruppen2.domain.event;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;
import mops.gruppen2.domain.Group;
import mops.gruppen2.domain.User;
import mops.gruppen2.domain.exception.BadParameterException;
import mops.gruppen2.domain.exception.EventException;

import java.util.UUID;

@Getter
@NoArgsConstructor
@ToString
@Log4j2
public class UpdateUserLimitEvent extends Event {

    private long userLimit;

    public UpdateUserLimitEvent(UUID groupId, String userId, long userLimit) {
        super(groupId, userId);
        this.userLimit = userLimit;
    }

    public UpdateUserLimitEvent(Group group, User user, long userLimit) {
        super(group.getId(), user.getId());
        this.userLimit = userLimit;
    }

    @Override
    protected void applyEvent(Group group) throws EventException {
        if (userLimit <= 0 || userLimit < group.getMembers().size()) {
            throw new BadParameterException("Usermaximum zu klein.");
        }

        group.setUserLimit(userLimit);

        log.trace("\t\t\t\t\tNeues UserLimit: {}", group.getUserLimit());
    }
}