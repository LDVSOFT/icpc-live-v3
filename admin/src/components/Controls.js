import React from "react";
import Container from "@mui/material/Container";

import "../App.css";
import { errorHandlerWithSnackbar } from "../errors";
import { useSnackbar } from "notistack";
import { PresetsManager } from "./PresetsManager";
import { ControlsService } from "../services/controls";

class ControlsManager extends PresetsManager {
    constructor(props) {
        super(props);
        this.loadData = () => {
            this.service.loadAll().then(elements =>
                this.setState(state => ({ ...state, dataElements: elements })));
        };
    }
}
ControlsManager.defaultProps = {
    ...PresetsManager.defaultProps,
    tableKeys: ["text"],
    isImmutable: true,
};


function Controls() {
    const { enqueueSnackbar, } = useSnackbar();
    return (
        <Container maxWidth="md" sx={{ pt: 2 }} className="Controls">
            <ControlsManager service={new ControlsService(errorHandlerWithSnackbar(enqueueSnackbar))}/>
        </Container>
    );
}

export default Controls;
