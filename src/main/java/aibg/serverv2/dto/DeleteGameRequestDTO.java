package aibg.serverv2.dto;


import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DeleteGameRequestDTO {
    private int gameId;
    private boolean training;
}
