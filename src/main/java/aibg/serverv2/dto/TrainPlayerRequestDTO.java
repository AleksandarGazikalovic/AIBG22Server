package aibg.serverv2.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TrainPlayerRequestDTO extends DTO {
    private int playerIdx;
    private long time;
}
