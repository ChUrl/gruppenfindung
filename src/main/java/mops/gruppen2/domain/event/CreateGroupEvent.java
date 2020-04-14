package mops.gruppen2.domain.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.extern.log4j.Log4j2;
import mops.gruppen2.domain.exception.BadArgumentException;
import mops.gruppen2.domain.model.group.Group;

import java.time.LocalDateTime;
import java.util.UUID;

@Log4j2
@Value
@AllArgsConstructor// Value generiert den allArgsConstrucot nur, wenn keiner explizit angegeben ist
public class CreateGroupEvent extends Event {

    @JsonProperty("date")
    LocalDateTime date;

    public CreateGroupEvent(UUID groupId, String exec, LocalDateTime date) {
        super(groupId, exec, null);
        this.date = date;
    }

    @Override
    protected void applyEvent(Group group) throws BadArgumentException {
        group.setId(groupid);
        group.setCreator(exec);
        group.setCreationDate(date);

        log.trace("\t\t\t\t\tNeue Gruppe: {}", group.toString());
    }

    @Override
    public String getType() {
        return EventType.CREATEGROUP.toString();
    }

    @Override
    public String toString() {
        return "(" + version + "," + groupid + "," + date + ")";
    }
}
