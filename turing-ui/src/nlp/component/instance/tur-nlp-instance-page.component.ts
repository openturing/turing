import { Component, OnInit } from '@angular/core';
import { Observable } from 'rxjs';
import { TurNLPInstance } from '../../model/instance/nlp-instance.model';
import { NotifierService } from 'angular-notifier';
import { TurNLPInstanceService } from '../../service/instance/nlp-instance.service';

@Component({
  selector: 'app-tur-nlp-instance-page',
  templateUrl: './tur-nlp-instance-page.component.html'
})
export class TurNLPInstancePageComponent implements OnInit {
  private turNLPInstances: Observable<TurNLPInstance[]>;

  constructor(private readonly notifier: NotifierService, private turNLPInstanceService: TurNLPInstanceService) {
    this.turNLPInstances = turNLPInstanceService.query();
  }

  getTurNLPInstances(): Observable<TurNLPInstance[]> {

    return this.turNLPInstances;
  }

  ngOnInit(): void {
  }
}
