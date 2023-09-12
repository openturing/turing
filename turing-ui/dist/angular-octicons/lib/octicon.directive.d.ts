import { OnInit, ElementRef, Renderer2 } from '@angular/core';
import { IconName } from '@primer/octicons';
import * as i0 from "@angular/core";
export declare class OcticonDirective implements OnInit {
    private elem;
    private renderer;
    iconName: IconName;
    color: string;
    size: number;
    constructor(elem: ElementRef, renderer: Renderer2);
    ngOnInit(): void;
    static ɵfac: i0.ɵɵFactoryDeclaration<OcticonDirective, never>;
    static ɵdir: i0.ɵɵDirectiveDeclaration<OcticonDirective, "[octicon]", never, { "iconName": "octicon"; "color": "color"; "size": "size"; }, {}, never, never, false>;
}
