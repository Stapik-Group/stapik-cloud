package pl.stapik.cloud.documentslot;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ValueMapping;
import pl.stapik.cloud.admin.data.DocumentSlotResponse;
import pl.stapik.cloud.documentslot.data.ConflictStrategy;
import pl.stapik.cloud.documentslot.data.DocumentSlotData;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface DocumentSlotMapper {

    DocumentSlotResponse toResponse(DocumentSlotData slot);

    @ValueMapping(target = "WINS", source = "LAST_WRITE_WINS")
    @ValueMapping(target = "WINS_WITH_SHADOW_COPY", source = "LAST_WRITE_WINS_WITH_SHADOW_COPY")
    pl.stapik.cloud.admin.data.ConflictStrategy mapConflictStrategy(ConflictStrategy conflictStrategy);
}