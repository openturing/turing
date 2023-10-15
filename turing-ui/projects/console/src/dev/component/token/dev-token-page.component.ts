import { Component, ElementRef, OnInit, ViewChild } from '@angular/core';
import { Observable } from 'rxjs';
import { NotifierService } from 'angular-notifier';
import { ActivatedRoute, Router } from '@angular/router';
import { UntypedFormControl, Validators } from '@angular/forms';
import {TurDevToken} from "../../model/dev-token.model";
import {TurDevTokenService} from "../../service/dev-token.service";

@Component({
  selector: 'dev-token-page',
  templateUrl: './dev-token-page.component.html'
})
export class TurDevTokenPageComponent implements OnInit {
  @ViewChild('modalDelete')
  modalDelete!: ElementRef;
  private turDevToken: Observable<TurDevToken>;
  private newObject: boolean = false;

  portControl = new UntypedFormControl(80, [Validators.max(100), Validators.min(0)])


  constructor(
    private readonly notifier: NotifierService,
    private turDevTokenService: TurDevTokenService,
    private activatedRoute: ActivatedRoute,
    private router: Router) {

    let id: string = this.activatedRoute.snapshot.paramMap.get('id') || "";
    this.newObject = (id != null && id.toLowerCase() === 'new');
    this.turDevToken = this.newObject ? this.turDevTokenService.getStructure() : this.turDevTokenService.get(id);
  }

  isNewObject(): boolean {
    return this.newObject;
  }

  saveButtonCaption(): string {
    return this.newObject ? "Create api token" : "Update api token";
  }

  getTurDevToken(): Observable<TurDevToken> {
    return this.turDevToken;
  }

  ngOnInit(): void {
  }

  public save(_turDevToken: TurDevToken) {
    this.turDevTokenService.save(_turDevToken, this.newObject).subscribe(
      (turDevToken: TurDevToken) => {
        let message: string = this.newObject ? "Token was created." : " Token was updated.";

        _turDevToken = turDevToken;

        this.notifier.notify("success", turDevToken.title.concat(message));

        this.router.navigate(['/dev/token']);
      },
      response => {
        this.notifier.notify("error", "Token was error: " + response);
      },
      () => {
        // console.log('The POST observable is now completed.');
      });
  }

  public delete(_turDevToken: TurDevToken) {
    this.turDevTokenService.delete(_turDevToken).subscribe(
      () => {
        this.notifier.notify("success", _turDevToken.title.concat(" Token was deleted."));
        this.modalDelete.nativeElement.removeAttribute("open");
        this.router.navigate(['/dev/token']);
      },
      response => {
        this.notifier.notify("error", "Token was error: " + response);
      });
  }
}
